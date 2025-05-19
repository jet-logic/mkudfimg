#!/bin/bash 
SCRIPT="$0"
# Catch common issue: script has been symlinked
if [ -L "$SCRIPT" ]
	then
	SCRIPT="$(readlink "$0")"
	# If link is relative
	case "$SCRIPT" in
		/*) ;; # fine
		*) SCRIPT=$( dirname "$0" )/$SCRIPT;; # fix
	esac
fi

BASE=$(dirname "$SCRIPT")

# java -ea -cp $BASE/../target/mkimg-1.0-alpha.jar mkimg.Main "$@"
java -ea -cp $BASE/../.build/mkimg-1.0-alpha-jar-with-dependencies.jar mkimg.Main "$@"

