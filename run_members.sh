#!/bin/bash

# Open 4 terminals for members
for i in {1..4}
do
  gnome-terminal -- bash -c "mvn exec:java -Dexec.mainClass='com.example.Member' -Dexec.args='member$i'; exec bash" &
done

# Open a terminal for the client
gnome-terminal -- bash -c "mvn exec:java -Dexec.mainClass='com.example.ClientLibrary'; exec bash" &
