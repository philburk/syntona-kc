## How to Build and Deploy to Website on SoftSynth.com

1. cd ~/work/syntona-kc
1. ./gradlew clean :webApp:wasmJsBrowserDistribution
1. open /Users/phil/Work/softsynth.github.io/src/syntona/
1. MAYBE delete old WASM app from the test folder
1. open webApp/build/dist/wasmJs/productionExecutable
1. copy contents to the softsynth syntona/test folder
1. commit change to the softsynth repo
1. wait for it to deploy: https://github.com/philburk/softsynth.github.io/deployments
1. test the app at https://softsynth.com/syntona/test/
1. if it works, deploy to the "beta" folder
1. if the beta seems OK for a few days then copy contents to the app folder

## How to build an Installable Desktop App with a DMG file

This should only be done by Phil Burk or another team member.

1. Enter:     ./gradlew :desktopApp:packageDmg
2. Wait for a few minutes while Apple notarizes the DMG file.
3. Open desktopApp/build/compose/binaries/main/dmg/
4. Create a new tagged release on GitHub and attach the DMG file.
