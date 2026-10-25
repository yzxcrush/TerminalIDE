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


def run_user(code, site_packages=None, stdin_text="", bridge=None):
    """Execute user code and return (exit_code, stdout, stderr).

    In interactive mode `bridge` drives stdin/stdout; in batch mode it uses
    `stdin_text` and captures output into memory.
    """
    ensure_sandbox_path(site_packages)

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
    try:
        exec(compile(code, "<editor>", "exec"), globals_dict)
    except SystemExit as exc:
        exit_code = _system_exit_code(exc)
    except BaseException:
        exit_code = 1
        traceback.print_exc(file=sys.stderr)
    finally:
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
