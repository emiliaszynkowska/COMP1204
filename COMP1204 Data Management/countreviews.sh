#shebang - shows the code is bash
#!/bin/bash

location=$1
echo "Path:" $location

#iterates through all the files in the reviews folder
for file in $location/*; do {

#prints the file name
#finds all occurences of the pattern <Author>
#-c counts the number of occurences
filename=$(basename $file .dat)
count=$(grep -c '<Author>' $file)
echo "$filename $count"

} done | sort -nr -k2 

#sorts in descending order of reviews
#-n means sort by numerical value
#-r means reverse
#-k2 means sort by the second column





