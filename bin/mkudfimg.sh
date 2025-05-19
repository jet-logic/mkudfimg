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

java -ea -cp "$BASE"/mkudfimg.jar mkudfimg.Main "$@"

