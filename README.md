The Asian game of Go takes place two-dimensional board. Its idea readily extends to three 
dimensions - placing stones on a cubic grid. 

This is a program for playing on such a three-dimensional grid. 

It runs under Java and needs the Java3D extension.

Under development: Port to Scala

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

## sbt project compiled with Scala 3

### Usage

This is a normal sbt project. You can compile code with `sbt compile`, run it with `sbt run`, and
`sbt console` will start a Scala 3 REPL.
