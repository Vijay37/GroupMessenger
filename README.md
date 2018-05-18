# GroupMessenger
An android application similar to whatsapp which can be used as a group messenger across five emulators. It makes use of B-Multicast and implements total ordering.

The port configuration used in the app is as follows: 
Each emulator listens on port 10000, but it will connect to a different port number on the IP address 10.0.2.2
for each emulator, as follows:
emulator serial port
emulator-5554 11108
emulator-5556 11112
emulator-5558 11116
emulator-5560 11120
emulator-5562 11124

This application provides ordering functionality of the messages sent to the group.
It provides ordered messages in the face of the failure of a single app instance during execution. This app provides a total ordering for incoming messages
among all app instances, and that total ordering should preserve FIFO ordering.

The URI of your content provider must be:
content://edu.buffalo.cse.cse486586.groupmessenger2.provider

Note: This app will handle at most one failure of an app instance during execution.
