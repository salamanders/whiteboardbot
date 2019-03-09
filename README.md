A LEGO EV3 setup with two motors winding strings that drag a dry erase marker around a whiteboard.

## TODO

### MQTT bidirectional communication with Firestore

* https://cloud.google.com/functions/docs/calling/cloud-firestore#deploying_your_function
* https://cloud.google.com/iot/docs/how-tos/commands#iot-core-send-command-nodejs



## Deploy

    mvn deploy
    firebase deploy
    gcloud functions deploy deviceToDb --runtime nodejs8 --trigger-resource target --trigger-event google.pubsub.topic.publish

## Reference

To SSH into the robot:

    ssh -oKexAlgorithms=+diffie-hellman-group1-sha1 root@192.168.86.250
    root@EV3:/home/lejos/programs# jrun -cp whiteboardbot-0.0.1-SNAPSHOT-jar-with-dependencies.jar info.benjaminhill.wbb.MainKt

* To view console Run ev3console or Eclipse: ev3control
* http://www.lejos.org/ev3/docs/

## Thanks To

https://www.marginallyclever.com/2012/02/drawbot-overview/
http://www.patriciogonzalezvivo.com/2014/vPlotter/
https://github.com/patriciogonzalezvivo/vPlotter
http://fabacademy.org/archives/2013/labs/amsterdam/class_15_machinedesign/math.html
