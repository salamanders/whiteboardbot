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

To SSH into the robot:

    ssh -oKexAlgorithms=+diffie-hellman-group1-sha1 root@192.168.86.250
    root@EV3:/home/lejos/programs# jrun -cp whiteboardbot-0.0.1-SNAPSHOT-jar-with-dependencies.jar info.benjaminhill.wbb.MainKt

Copying files

    scp -oKexAlgorithms=+diffie-hellman-group1-sha1 ./alpn-boot-8.1.13.v20181017.jar  root@192.168.86.250:/home/root/lejos/lib/

To view console Run ev3console or Eclipse: ev3control
http://www.lejos.org/ev3/docs/

Dev Env Setup
    
    gcloud components update && gcloud components install beta
    npm install -g firebase-tools

To recreate the runtime


    # Download from http://www.oracle.com/technetwork/java/embedded/downloads/java-embedded-java-se-download-359230.html
    # NOTE: The "-g" is from [stack overflow](https://stackoverflow.com/questions/23275519/jdwp-in-embedded-jre-in-java-8)
    gunzip ejdk-8-fcs-b132-linux-arm-sflt-03_mar_2014.tar.gz
    tar xvf ejdk-8-fcs-b132-linux-arm-sflt-03_mar_2014.tar
    cd ejdk1.8.0/bin
    export JAVA_HOME=/usr
    ./jrecreate.sh -g --dest ../../ejre-8u1-linux-arm-15_may_2015 --profile compact2 --vm client
    cd ../..
    tar cvf ejre-8u1-linux-arm-15_may_2015.tar ejre-8u1-linux-arm-15_may_2015
    gzip ejre-8u1-linux-arm-15_may_2015.tar


## Thanks To

* https://www.marginallyclever.com/2012/02/drawbot-overview/
* http://www.patriciogonzalezvivo.com/2014/vPlotter/
* https://github.com/patriciogonzalezvivo/vPlotter
* http://fabacademy.org/archives/2013/labs/amsterdam/class_15_machinedesign/math.html
* https://gist.github.com/pfdevmuller/473d03765906f5e25791


