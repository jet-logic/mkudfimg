@if not exist "%~dp0mkudfimg.jar" (
  @echo mkudfimg.jar not found in %~dp0
  @exit /b 1
@)
@java -ea -cp "%~dp0mkudfimg.jar" mkudfimg.Main %*
