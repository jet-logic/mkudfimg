@if not exist "%~dp0mkimg.jar" (
  @echo mkimg.jar not found in %~dp0
  @exit /b 1
@)
@java -ea -cp "%~dp0mkimg.jar" mkimg.Main %*
