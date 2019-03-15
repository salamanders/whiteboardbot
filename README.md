A LEGO EV3 setup with two motors winding strings that drag a dry erase marker around a whiteboard.

## Architecture

Firestore:

* /devices/{id=ev3}
* /telemetry/{id=ev3} 
* https://console.firebase.google.com/project/whiteboardbot/database/firestore/data~2Fwbb~2Fboard01

## TODO

### MQTT bidirectional communication with Firestore

* https://cloud.google.com/community/tutorials/cloud-iot-firestore-config
* https://cloud.google.com/functions/docs/calling/cloud-firestore#deploying_your_function
* https://cloud.google.com/iot/docs/how-tos/commands#iot-core-send-command-nodejs

## Deploy

    mvn deploy
    firebase deploy
    gcloud functions deploy deviceToDb --runtime nodejs8 --trigger-resource target --trigger-event google.pubsub.topic.publish

## Reference


Dev Env Setup
    
    gcloud components update && gcloud components install beta
    npm install -g firebase-tools



## Thanks To

* https://www.marginallyclever.com/2012/02/drawbot-overview/
* http://www.patriciogonzalezvivo.com/2014/vPlotter/
* https://github.com/patriciogonzalezvivo/vPlotter
* http://fabacademy.org/archives/2013/labs/amsterdam/class_15_machinedesign/math.html
* https://gist.github.com/pfdevmuller/473d03765906f5e25791


