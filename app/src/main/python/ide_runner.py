"""Runtime helpers for executing user code inside the embedded CPython (Chaquopy).

Kept intentionally small: IO redirection / interactive stdin, sandbox site-packages
injection and exception capture live here. Package installation goes through `pip_runner`.

Interactive mode (bridge != None):
  - sys.stdin.readline() blocks until the UI sends a line (Java LinkedBlockingQueue)
  - sys.stdout / sys.stderr stream back to the UI in real time
Batch mode (bridge == None):
  - sys.stdin reads from `stdin_text`, output is captured in memory
"""

import io
import time
import sys
import traceback


def ensure_sandbox_path(site_packages):
    """Put the local sandbox site-packages at the front of sys.path so that
    packages installed at runtime can be imported without touching the system."""
    if site_packages and site_packages not in sys.path:
        sys.path.insert(0, site_packages)


class _BridgeStdin(io.TextIOBase):
    """sys.stdin wrapper: readline() blocks waiting for a line fed by the UI."""

    def __init__(self, bridge):
        self._bridge = bridge
        self._buf = ""

    def readable(self):
        return True

    def readline(self, size=-1):
        if self._buf:
            idx = self._buf.find("\n")
            if idx < 0:
                line, self._buf = self._buf, ""
                return line
            line, self._buf = self._buf[: idx + 1], self._buf[idx + 1 :]
            return line
        line = self._bridge.readLine()
        if line is None:
            return ""  # EOF
        return line if line.endswith("\n") else line + "\n"

    def read(self, size=-1):
        return self.readline()


class _BridgeWriter:
    """sys.stdout/stderr wrapper: streams written text to the UI in real time."""

    def __init__(self, bridge, is_err=False):
        self._bridge = bridge
        self._is_err = is_err

    def write(self, s):
        if s:
            text = str(s)
            if self._is_err:
                self._bridge.emitErr(text)
            else:
                self._bridge.emitOut(text)
        return len(s)

    def flush(self):
        pass

    def isatty(self):
        return False


def run_user(code, site_packages=None, stdin_text="", bridge=None, workspace=None):
    """Execute user code and return (exit_code, stdout, stderr).

    In interactive mode `bridge` drives stdin/stdout; in batch mode it uses
    `stdin_text` and captures output into memory.
    """
    ensure_sandbox_path(site_packages)
    _ABORT["flag"] = False

    # 工作区加入 sys.path：支持 import 同目录/子目录的模块（多文件项目）
    if workspace and workspace not in sys.path:
        sys.path.insert(0, workspace)

    globals_dict = {"__name__": "__main__", "__builtins__": __builtins__}
    old_out, old_err, old_in = sys.stdout, sys.stderr, sys.stdin
    out = None
    err = None
    if bridge is not None:
        sys.stdout = _BridgeWriter(bridge, False)
        sys.stderr = _BridgeWriter(bridge, True)
        sys.stdin = _BridgeStdin(bridge)
    else:
        out = io.StringIO()
        err = io.StringIO()
        sys.stdout, sys.stderr = out, err
        sys.stdin = io.StringIO(stdin_text or "")

    exit_code = 0
    old_trace = sys.gettrace()
    # 每行检查中断标志（纯 Python 死循环只能靠 trace 打断）
    sys.settrace(_trace)
    try:
        exec(compile(code, "<editor>", "exec"), globals_dict)
    except _UserAbort:
        exit_code = 130
        print("\n[已手动停止]", file=sys.stderr)
    except SystemExit as exc:
        exit_code = _system_exit_code(exc)
    except BaseException:
        exit_code = 1
        traceback.print_exc(file=sys.stderr)
    finally:
        sys.settrace(old_trace)
        try:
            _snapshot_vars(globals_dict)
        except Exception:
            pass
        sys.stdout, sys.stderr, sys.stdin = old_out, old_err, old_in

    if out is not None:
        return exit_code, out.getvalue(), err.getvalue()
    return exit_code, "", ""


def _system_exit_code(exc):
    if exc.code is None:
        return 0
    if isinstance(exc.code, int):
        return exc.code
    return 1


# ==================== 变量快照 ====================
_LAST_VARS = {"json": "[]"}


def _snapshot_vars(ns):
    """记录运行后的变量（只保留可序列化的简单类型），供「变量检查器」展示。"""
    import json as _json
    out = []
    for k, v in ns.items():
        if k.startswith("__") or callable(v) or getattr(v, "__module__", None) not in (None, "builtins"):
            continue
        if isinstance(v, (int, float, bool, str, list, dict, tuple, set)):
            try:
                out.append({"name": k, "value": repr(v)[:200]})
            except Exception:
                pass
    _LAST_VARS["json"] = _json.dumps(out, ensure_ascii=False)


def get_last_vars():
    """返回上次运行的变量列表（JSON 字符串）。"""
    return _LAST_VARS["json"]


# ==================== 中断支持 ====================
# 通过 sys.settrace 在每行代码执行时检查中断标志，这是中断纯 Python 死循环的唯一办法
_ABORT = {"flag": False}


class _UserAbort(BaseException):
    """用户主动中断（继承 BaseException，避免被用户的 except Exception 吞掉）。"""
    pass


def abort_exec():
    """请求中断正在执行的用户代码（UI「停止」按钮调用）。"""
    _ABORT["flag"] = True
    return True


def _trace(frame, event, arg):
    if _ABORT["flag"]:
        raise _UserAbort()
    return _trace


# REPL 超时支持
_REPL_DEADLINE = {"t": 0}
_REPL_COUNTER = [0]


def _repl_trace(frame, event, arg):
    if _ABORT["flag"]:
        raise _UserAbort()
    _REPL_COUNTER[0] += 1
    # 每 1024 个事件检查一次时间，降低开销
    if (_REPL_COUNTER[0] & 0x3FF) == 0 and time.time() > _REPL_DEADLINE["t"]:
        raise _UserAbort()
    return _repl_trace


# ==================== REPL ====================
# 持久命名空间：跨多次 repl_exec 保留变量/导入
_REPL_NS = {"__name__": "__main__", "__builtins__": __builtins__}


def repl_exec(code):
    """REPL 单次执行：在持久命名空间里执行一行/一段代码，返回捕获到的输出文本。

    先按表达式 eval（自动打印 repr），语法不合法再按语句 exec，
    这样一个输入框既能算 `1+1` 也能写 `for ...:`。
    """
    out = io.StringIO()
    old_out, old_err = sys.stdout, sys.stderr
    sys.stdout, sys.stderr = out, out
    _ABORT["flag"] = False
    # REPL 单次执行最多 60 秒，防止 while True 卡死运行时
    _REPL_DEADLINE["t"] = time.time() + 60
    old_trace = sys.gettrace()
    sys.settrace(_repl_trace)
    try:
        try:
            value = eval(compile(code, "<repl>", "eval"), _REPL_NS)
            if value is not None:
                print(repr(value))
        except SyntaxError:
            exec(compile(code, "<repl>", "exec"), _REPL_NS)
    except _UserAbort:
        print("[执行超时或已中断]")
    except SystemExit:
        print("[已退出]")
    except BaseException:
        traceback.print_exc()
    finally:
        sys.settrace(old_trace)
        sys.stdout, sys.stderr = old_out, old_err
    return out.getvalue()


def repl_reset():
    """清空 REPL 命名空间（保留 __builtins__）。"""
    _REPL_NS.clear()
    _REPL_NS.update({"__name__": "__main__", "__builtins__": __builtins__})
    return True



# ============ 脚本变 App：工具扫描与调用 ============

def list_tools(workspace):
    """扫描工作区 .py 文件里的顶层函数，返回 JSON 描述（供 UI 生成表单）。"""
    import ast, os, json
    tools = []
    try:
        names = sorted(os.listdir(workspace))
    except Exception:
        return "[]"
    for name in names:
        if not name.endswith(".py"):
            continue
        path = os.path.join(workspace, name)
        try:
            src = open(path, encoding="utf-8", errors="replace").read()
            tree = ast.parse(src)
        except Exception:
            continue
        module_doc = ast.get_docstring(tree) or ""
        for node in tree.body:
            if not isinstance(node, ast.FunctionDef):
                continue
            if node.name.startswith("_"):
                continue
            params = []
            for a in node.args.args:
                ptype = "str"
                if a.annotation is not None:
                    try:
                        ptype = ast.unparse(a.annotation)
                    except Exception:
                        pass
                params.append({"name": a.arg, "type": ptype})
            tools.append({
                "file": name,
                "func": node.name,
                "params": params,
                "doc": (ast.get_docstring(node) or module_doc or "").strip().split("\n")[0][:80],
            })
    return json.dumps(tools, ensure_ascii=False)


def run_tool(workspace, file_name, func_name, kwargs_json):
    """调用某个工具函数，捕获 stdout 与返回值。"""
    import json, os, io, contextlib, traceback
    path = os.path.join(workspace, file_name)
    try:
        src = open(path, encoding="utf-8", errors="replace").read()
        ns = {"__name__": "__tool__"}
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            exec(compile(src, path, "exec"), ns)
            fn = ns[func_name]
            result = fn(**json.loads(kwargs_json))
        out = buf.getvalue()
        text = result if isinstance(result, str) else repr(result)
        combined = out + (("\n" if out and text else "") + text if text else "")
        return combined[:200000] or "(无输出)"
    except Exception:
        return "错误：\n" + traceback.format_exc()[-4000:]


def run_script(workspace, file_name):
    """后台运行整个脚本文件，返回 stdout 文本。"""
    import io, os, contextlib, traceback
    path = os.path.join(workspace, file_name)
    try:
        src = open(path, encoding="utf-8", errors="replace").read()
        ns = {"__name__": "__main__"}
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            exec(compile(src, path, "exec"), ns)
        return buf.getvalue()[:200000] or "(无输出)"
    except Exception:
        return "错误：\n" + traceback.format_exc()[-4000:]
