[![Build Status](https://travis-ci.org/salesforce/proto-backwards-compat-maven-plugin.svg?branch=master)](https://travis-ci.org/salesforce/proto-backwards-compat-maven-plugin) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.salesforce.servicelibs/proto-backwards-compatibility.svg)](https://maven-badges.herokuapp.com/maven-central/com.salesforce.servicelibs/proto-backwards-compatibility)

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
by deleting the proto.lock file. It will then reinitialize the next time the
plugin is run.

#### Maintaining Backwards Compatibility
In order to maintain backwards compatibility for your set of .proto files, a few
rules must be followed when making updates. Please refer here to avoid breaking changes:
https://developers.google.com/protocol-buffers/docs/proto#backwards-compatibility

## Configuration

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

## Acknowledgements
Thank you to Steve Manuel for his protocol buffer compatiblity tracker which
is a key component of this plugin: https://github.com/nilslice/protolock
