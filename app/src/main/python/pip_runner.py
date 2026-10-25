"""Runtime pip driver for the embedded CPython (Chaquopy).

Invokes the bundled `pip` in-process (no subprocess), forcing:
  * install into the sandbox site-packages via `--target`
  * the selected mirror via `--index-url`
  * Chaquopy's prebuilt native-wheel repo via `--extra-index-url`
  * `--only-binary :all:` so no C extension is ever compiled on-device

All stdout/stderr are captured to strings and returned to the Kotlin side.
"""

import io
import sys
import traceback


def ensure_sandbox_path(site_packages):
    """Put the sandbox site-packages on sys.path (also lets pip uninstall from it)."""
    if site_packages and site_packages not in sys.path:
        sys.path.insert(0, site_packages)


def install(package, site_packages, index_url, extra_index_url=None):
    ensure_sandbox_path(site_packages)

    args = [
        "install",
        "--target", site_packages,
        "--upgrade",
        "--no-input",
        "--no-cache-dir",
        "--only-binary", ":all:",
        "--index-url", index_url,
    ]
    if extra_index_url:
        args += ["--extra-index-url", extra_index_url]

    args += [package]
    return _invoke(args)


def uninstall(package, site_packages):
    ensure_sandbox_path(site_packages)
    return _invoke(["uninstall", "--yes", "--no-input", package])


def _invoke(args):
    out = io.StringIO()
    err = io.StringIO()
    old_out, old_err = sys.stdout, sys.stderr
    sys.stdout, sys.stderr = out, err

    exit_code = 0
    try:
        # pip 10+ 的稳定入口，等价于 `python -m pip`
        from pip._internal.cli.main import main as pip_main
        exit_code = pip_main(args) or 0
    except BaseException:
        exit_code = 1
        traceback.print_exc(file=err)
    finally:
        sys.stdout, sys.stderr = old_out, old_err

    return exit_code, out.getvalue(), err.getvalue()
