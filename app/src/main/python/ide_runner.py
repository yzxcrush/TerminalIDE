"""Runtime helpers for executing user code inside the embedded CPython (Chaquopy).

Kept intentionally small: only IO redirection, sandbox site-packages injection
and exception capture live here. Package installation goes through `pip_runner`.
"""

import io
import sys
import traceback


def ensure_sandbox_path(site_packages):
    """Put the local sandbox site-packages at the front of sys.path so that
    packages installed at runtime can be imported without touching the system."""
    if site_packages and site_packages not in sys.path:
        sys.path.insert(0, site_packages)


def run_user(code, site_packages=None, stdin_text=""):
    """Execute user code and return (exit_code, stdout, stderr).

    sys.stdout/stderr are swapped to StringIO and restored afterwards, so the
    surrounding app is unaffected and we capture every `print` / traceback.
    `sys.stdin` is swapped to a StringIO fed by [stdin_text] so that `input()`
    reads the lines the user typed in the editor's stdin box.
    """
    ensure_sandbox_path(site_packages)

    globals_dict = {"__name__": "__main__", "__builtins__": __builtins__}
    out = io.StringIO()
    err = io.StringIO()
    old_out, old_err, old_in = sys.stdout, sys.stderr, sys.stdin
    sys.stdout, sys.stderr = out, err
    sys.stdin = io.StringIO(stdin_text or "")

    exit_code = 0
    try:
        exec(compile(code, "<editor>", "exec"), globals_dict)
    except SystemExit as exc:
        exit_code = _system_exit_code(exc)
    except BaseException:
        exit_code = 1
        traceback.print_exc(file=err)
    finally:
        sys.stdout, sys.stderr, sys.stdin = old_out, old_err, old_in

    return exit_code, out.getvalue(), err.getvalue()


def _system_exit_code(exc):
    if exc.code is None:
        return 0
    if isinstance(exc.code, int):
        return exc.code
    return 1
