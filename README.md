# NonEuclid

[![Circle CI](https://circleci.com/gh/AgentME/NonEuclid.svg?style=shield)](https://circleci.com/gh/AgentME/NonEuclid)

This is a Bukkit plugin for Minecraft servers that allows overlapping pathways
to be configured.

[![Demonstration video](extra/noneuclid.gif)](https://raw.githubusercontent.com/AgentME/NonEuclid/master/extra/noneuclid.webm)

*Click for a higher-quality video clip*

Clients treat the wall blocks as real, but to the server the walls are only
illusionary. They are never saved to the world file. The walls displayed by
this plugin do not affect non-player entities including arrows and thrown
ender pearls. The plugin stops mobs from targeting players through the
illusionary walls in certain cases, but they may still pathfind and walk
through them.

Here's a birds-eye diagram giving a barebones example of how an overlapping
pathway works. The dots represent empty spots. P1 and P2 are two players.
The center of the intersection is in the middle between the O and X characters.
The P1 player will see walls where the X characters are. The P2 player will see
walls where the O characters are. If P1 walks to P2's location, they will see
the X walls disappear and the O walls appear. If instead P1 walks directly
into the center, crossing the O walls (that only P2 sees), then P1 will become
invisible to P2 as long as P1 is inside of the intersection in the path
opposite of what P2 sees.

    . . . P1. . .
    . . . . . . .
    . . . . . . .
    . O O . . . .
    X . . X . P2.
    X . . X . . .
    . O O . . . .

Ideally, you would build a structure around the O and X walls so that a player
will never see the change, and will never see the backside of either.

## Installing

This plugin depends on
[ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/). You must
download its jar and place it in your plugins/ directory.

Download the latest NonEuclid jar file from the
[project's Releases page](https://github.com/AgentME/NonEuclid/releases).
Place the jar file in the plugins/ directory. After the first run, the file
plugins/NonEuclid/config.yml file will be generated with default values.

## Configuration

By default, no overlapping locations are enabled. Multiple overlapping
locations may be specified.

You create an overlapping pathway by specifying the center of the intersection,
and then the height, width, and material for the illusionary walls in the
config.

The config has a global `max_distance` setting, and individual locations may
override it.

The `disabled` setting allows a location to be easily left in the config while
disabled. The setting may be removed.

If a player connects to the server while standing inside of an intersection,
then the pathway specified by the `default_path` setting will be displayed.
This value may be "NorthSouth" or "EastWest".

This plugin records some usage metrics to
https://bstats.org/plugin/bukkit/NonEuclid. You can opt out of this by
placing `enabled: false` in `plugins/bStats/config.yml`.
