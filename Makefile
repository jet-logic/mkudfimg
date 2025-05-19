### CMDREF.PL
.build/cmdref.xml: doc/cmdref.src.xml
	dbproc --version
	dbproc --type=cmdref --src doc/cmdref.src.xml --out $@
.build/manual.html: doc/*.x*l .build/cmdref.xml
	dbproc --version
	dbproc --type=html --src doc/manual.xml --out $@
cutdoc: .build/manual.html
	lynx -version | echo
	lynx -nolist -dump -width=80 $? | perl -ne "if(/\S+/){print}" | gclip
man: .build/manual.html
### CMDREF.PL
