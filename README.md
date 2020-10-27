[![Build Status](https://travis-ci.org/salesforce/proto-backwards-compat-maven-plugin.svg?branch=master)](https://travis-ci.org/salesforce/proto-backwards-compat-maven-plugin) 
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.salesforce.servicelibs/proto-backwards-compatibility/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.salesforce.servicelibs/proto-backwards-compatibility)

Protolock Version: [20190714T165210Z](https://github.com/nilslice/protolock/releases/tag/v0.14.0)

# Protobuf Backwards Compatibility Check Maven Plugin

The <code>proto-backwards-compatibility</code> plugin is a Maven plugin to
run a backwards compatibility check on a set of protobuf IDL files. The plugin
can be integrated into various phases of a maven build to check that any changes to
a set of .proto files are backwards compatible. This ensures that these changes do
not impact existing users that haven't had a chance to update to these latest changes.
A proto.lock file is created in the proto source directory to keep
track of the state of the .proto files. This file is updated when a non-breaking change
is made, and should be checked in along with any other changes.

It is also possible to force any breaking changes and reset the current state
by either 
* deleting the proto.lock file. It will then reinitialize the next time the
plugin is run.
* or by specifying parameter property `acceptBreakingChanges` (`-DacceptBreakingChanges=true`)

#### Maintaining Backwards Compatibility
In order to maintain backwards compatibility for your set of .proto files, a few
rules must be followed when making updates. Please refer here to avoid breaking changes:
https://developers.google.com/protocol-buffers/docs/proto#backwards-compatibility

## Usage

The <code>os-maven-plugin</code> extension (https://github.com/trustin/os-maven-plugin) 
must be added to your project's pom.xml file in order for this plugin to work.

```xml
<build>
    <extensions>
        <extension>
            <groupId>kr.motd.maven</groupId>
            <artifactId>os-maven-plugin</artifactId>
            <version>1.4.1.Final</version>
        </extension>
    </extensions>
    <plugins>
        <plugin>
            <groupId>com.salesforce.servicelibs</groupId>
            <artifactId>proto-backwards-compatibility</artifactId>
            <version>${proto-backwards-compatibility.version}</version>
            <configuration>
                <!-- Optional alternative protos location -->
                <protoSourceRoot>src/main/proto</protoSourceRoot>
                <!-- Optional alternative proto.lock location -->
                <lockDir>src/main/proto</lockDir>
            </configuration>
            <executions>
                <execution>
                    <goals>
                        <goal>backwards-compatibility-check</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

## Configuration

* `<protoSourceRoot>` (`${basedir}/src/main/proto`) - The directory where proto sources can be found.
* `<lockDir>` (defaults to root of proto files) - The directory where proto.lock will be kept.
* `<options>` (empty) - Additional [command line options](https://github.com/nilslice/protolock#usage) to pass to protolock.

```xml
<configuration>
    <plugins>
        <protoSourceRoot>src/main/proto</protoSourceRoot>
        <options>--ignore=target/</options>
        <lockDir>${project.basedir}</lockDir>
    </plugins>
</configuration>
```

## Plugins
Protolock has [plugin support](https://github.com/nilslice/protolock/wiki/Plugins), to allow
for new rules to be enforced. Protolock plugins are referenced by adding the following
additional configuration to your pom.xml.

```xml
<configuration>
    <plugins>
        <!-- Plugin executable distributed by Maven -->
        <plugin>com.salesforce.servicelibs:sample-plugin:0.1.0-SNAPSHOT:${os.detected.classifier}</plugin>
        <!-- Plugin executable found on the system path -->
        <plugin>sample-plugin</plugin>
    </plugins>
</configuration>
```

## Acknowledgements
Thank you to Steve Manuel for his protocol buffer compatiblity tracker which
is a key component of this plugin: https://github.com/nilslice/protolock
