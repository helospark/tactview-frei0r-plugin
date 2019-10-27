g++ -DDEBUG_LOGGING=true -DDEBUG=true -w *.cpp -ldl -g
g++ -DDEBUG_LOGGING=true -w -shared -fPIC -Wl,-soname,libfreiorplugin.so -o libfreiorplugin.so *.cpp -ldl -g
