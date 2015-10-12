## Haptic Feedback Case: Android Application
> The haptic feedback case is a shell that attaches to most modern android phones, it interfaces the user through positional and pressure input on the sides and rear of their device.   

> This application is designed and developed by Ben Kazemi in 2015, and accompanies the firmware in its associated repository. 

## Dependencies
- usbSerialForAndroid by mik3y
- Smartphone with OTG capabilities 

## Build
build an apk from android studio and install it from within the host device

## TODO List:
- [x] Android App: adapt app to send these RX signals for mode and power selection (need to keep track of rx mode) [new state in FSM]
- [ ] restrict any Rx from host to limimt of 254 to not cause unpredicted behaviour from client 
- [ ] Android App: refactor app 
- [ ] Android App: Make a service which allows apps to query sensor state/values without needing to manipulate usb port

## License 
This code is available under the GNU V3 license. 

# HapticCaseAndroidApp
This application accompanies the firmware in its associated repository. 