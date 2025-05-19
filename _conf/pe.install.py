if profile.test("os.shell", "cmd"):
	if profile.test("os.name", "posix"):
		Directory("bin").call(target=Resource("../bin/mkimg.cmd"))
elif profile.test("os.shell", "sh"):
	if profile.test("os.name", "posix"):
		Directory("bin").execute(target=Resource("../bin/mkimg.sh"), name="mkimg")
