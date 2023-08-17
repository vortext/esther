#!/bin/bash
for file in *.ttf
do
    ./convert_to_woff.pe $file
done

