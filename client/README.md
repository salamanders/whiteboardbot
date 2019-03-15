

LeJOS clients are fun!  Fix the brick's IP address in pom.xml, and run maven:deploy 

    mvn deploy
    mvn antrun:run

To SSH into the robot:

    ssh -oKexAlgorithms=+diffie-hellman-group1-sha1 root@192.168.86.250
    root@EV3:/home/lejos/programs# jrun -cp whiteboardbot-0.0.1-SNAPSHOT-jar-with-dependencies.jar info.benjaminhill.wbb.MainKt

Copying files

    scp -oKexAlgorithms=+diffie-hellman-group1-sha1 ./x.jar root@192.168.86.250:/home/root/lejos/lib/

To view console Run ev3console or Eclipse: ev3control
http://www.lejos.org/ev3/docs/

    mvn versions:display-dependency-updates
    mvn dependency:copy-dependencies
    
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
