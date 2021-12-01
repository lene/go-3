The Asian game of Go takes place two-dimensional board. Its idea readily extends to three 
dimensions - placing stones on a cubic lattice. 

This is a program for playing on such a three-dimensional lattice. 

Installing
==========

Running
=======
Server
------
From docker image (setting the savegames folder to `$SAVE_DIR` and the port to `$PORT`):
```
$ docker run [--net=host] [--env SAVE_DIR=$SAVE_DIR] [--env PORT=$PORT] \
    -t registry.gitlab.com/lilacashes/go-3/server:latest
```
From a local install:
``` 
$ runner --server [--port $PORT] [--save-dir $SAVE_DIR]
```

Client
------

To start a new game with board size `$SIZE`:
```
$ ascii-client --server $SERVER --port 6030 --size $SIZE --color [b|w]
```
To register as second player for a game with ID `$GAME_ID`:
```
$ ascii-client --server $SERVER --port 6030 --game-id $GAME_ID --color [b|w]
```
To reconnect to a game that has already started and the player has been given the authentication
token `$TOKEN`:
```
$ ascii-client --server $SERVER --port 6030 --game-id $GAME_ID --token $TOKEN
```
The client will display a spinning prompt while waiting for the other client to make their move.

Once a client is ready to make a move, the command `s $X $Y $Z` will set a stone at position 
(`$X`, `$Y`, `$Z`), or the command `p` will pass.

Development notes
=================

Installing `sbt`
----------------

See https://www.scala-sbt.org/1.x/docs/Installing-sbt-on-Linux.html for full instructions.

### Shortcut for Ubuntu
```shell
echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | sudo tee /etc/apt/sources.list.d/sbt_old.list
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo apt-key add
sudo apt-get update
sudo apt-get install sbt
```

### Troubleshooting

```shell
$ sbt compile
...
[error] java.io.IOException: User limit of inotify instances reached or too many open files
...
```
can be solved with:
```shell
$ cat /proc/sys/fs/inotify/max_user_instances
128
$ echo 256 | sudo tee /proc/sys/fs/inotify/max_user_instances             0.000s
256
```
or for a longer term solution (?)
```shell
$ sudo sysctl fs.inotify.max_user_instances=256
```
