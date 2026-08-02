## Build and Deploy to Website on SoftSynth.com

1. cd ~/work/syntona-kc
1. ./gradlew :webApp:wasmJsBrowserDistribution
1. open /Users/phil/Work/softsynth.github.io/src/syntona/app
1. delete contents of the app folder
1. open syntona-kc/webApp/build/dist/wasmJs/productionExecutable
1. copy contents to the softsynth syntona/app folder
1. commit change to the softsynth repo
