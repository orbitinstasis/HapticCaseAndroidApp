<<<<<<< HEAD
## Haptic Feedback Case: Android Application
> The haptic feedback case is a shell that attaches to most modern android phones, it interfaces the user through positional and pressure input on the sides and rear of their device.   

> This application is designed and developed by Ben Kazemi. 

## Dependencies
- Android Studio
- Smartphone with OTG capabilities 

## Build
build an apk from android studio and install it from within the host device

## TODO List:
- [ ] Android App: adapt app to send these RX signals for mode and power selection (need to keep track of rx mode) [new state in FSM]
- [ ] restrict any Rx from host to limimt of 254 to not cause unpredicted behaviour from client 
- [ ] when requesting a sensor, the client echos your request back. assert on the host that it is correct else return an error
- [ ] Android App: refactor app 
- [ ] Android App: look into making a service

## License 

This code is available under the GNU V3 license. 
=======
# HapticCaseAndroidApp
This application accompanies the firmware in its associated repository. 
>>>>>>> 947e78ed786f031c6e6c0ab84cc82864a074e40c
