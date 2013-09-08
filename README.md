ASM Late-Binding Agent
=====================

A demo late binding Java agent utilising ASM for transforming classes.

A simple library, it has a deliciously simple API for injecting code into already loaded classes.

Compilation
-----------

We use [Maven](http://maven.apache.org/download.html) to handle our dependencies.

Usage
-----

There are two classes to be concerned with: `AgentLoader` and `Tools`. `AgentLoader` contains the meat of the injector,
while `Tools` holds various IO helpers.

To load an agent, its as simple as:

```java
    Tools.loadAgentLibrary(); // Loads the OS-specific attach library
    // Loads the agent in `Agent.class`, and packages `AgentLoader.class` as a dependency
    AgentLoader.attachAgentToJVM(Tools.getCurrentPID(), Agent.class, AgentLoader.class);
```