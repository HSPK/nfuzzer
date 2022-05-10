ps -aux | grep 'instrument' | grep -v 'grep' | awk '{print $2}' | xargs kill
