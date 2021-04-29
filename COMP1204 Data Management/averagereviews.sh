#!/bin/bash

location=$1
echo "Path: $location"

#iterates over each file in the reviews folder
for file in $location/*; do {

    #prints the filename
    #finds all values for the overall rating
    #finds the average by dividing the total over the number of values
    #rounds to 2 decimal places
    filename=$(basename $file .dat)
	count=0; total=0;
	a=$(grep -E '<Overall>' $file | sed 's/<Overall>//' | awk '//{count++; total+=$0} END {print total/count}')
	average=$(printf "%0.2f" $a)
	echo "$filename $average"

} done | sort -nr -k2 

#sorts the averages in descending order
#-n means sort by numerical value
#-r means reverse order
#-k2 means sort by the second column