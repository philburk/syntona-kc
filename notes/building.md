## Build and Deploy to Website on SoftSynth.com

1. cd ~/work/syntona-kc
1. ./gradlew :webApp:wasmJsBrowserDistribution
1. open /Users/phil/Work/softsynth.github.io/src/syntona/
1. delete contents of the test folder
1. open webApp/build/dist/wasmJs/productionExecutable
1. copy contents to the softsynth syntona/test folder
1. commit change to the softsynth repo
1. wait for it to deploy: https://github.com/philburk/softsynth.github.io/deployments
1. test the app at https://softsynth.com/syntona/test/
1. if it works, deploy to the "beta" folder
1. if the beta seems OK for a few days then copy contents to the app folder
