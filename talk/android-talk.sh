#!/bin/bash
set -o errexit

cheese &
disown

~/eclipse/eclipse

wc_LifeCamDeviceName="`uvcdynctrl -l | grep -e Microsoft | cut -d \  -f 3`"
uvcdynctrl -d "$wc_LifeCamDeviceName" -s Exposure,\ Auto 1
uvcdynctrl -d "$wc_LifeCamDeviceName" -s Exposure\ \(Absolute\) 9
uvcdynctrl -d "$wc_LifeCamDeviceName" -s Focus,\ Auto 0
uvcdynctrl -d "$wc_LifeCamDeviceName" -s Focus\ \(absolute\) 13

