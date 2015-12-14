## Haptic Feedback Case: Android Application
> The haptic feedback case is a shell that attaches to most modern android phones, it interfaces the user through positional and pressure input on the sides and rear of their device.   

This app in its current state is unoptimised though complements all of the firmwares functionalities for the given demo apps. 
An optimised code base can be found in the windows port of this application. A port frmom the new optimised windows app to an android app will be made when appropriate. 

> This application is designed and developed by Ben Kazemi in 2014/2015. 

## Dependencies
- usbSerialForAndroid by mik3y

## Build
build an apk from android studio and install it on the host device

## TODO List:
- [x] Android App: adapt app to send these RX signals for mode and power selection (need to keep track of rx mode) [new state in FSM]
- [x] Update android app to work with new sleep/dynamic baud rate systems
- [ ] Android App: port windows app optimised codebase to android
- [ ] Android App: Make a service which scrolls the screen

## License 
This code is available under the GNU V3 license. 

# HapticCaseAndroidApp
This application accompanies the firmware in its associated repository. 