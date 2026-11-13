#!/usr/bin/env python3
"""Builds roblox/QuikStop.rbxlx: a Roblox Studio place file with both scripts embedded.
Open it in Roblox Studio, press Play to test, File > Publish to Roblox to upload."""
import pathlib
root = pathlib.Path(__file__).parent / "roblox"
server = (root / "Server.server.luau").read_text()
client = (root / "Client.client.luau").read_text()
for name, src in (("server", server), ("client", client)):
    assert "]]>" not in src, name + " contains ]]>"

n = [0]
def ref():
    n[0] += 1
    return "RBX%d" % n[0]

def script(cls, name, src):
    return ('<Item class="%s" referent="%s"><Properties><string name="Name">%s</string>'
            '<ProtectedString name="Source"><![CDATA[%s]]></ProtectedString></Properties></Item>'
            % (cls, ref(), name, src))

def service(cls, children="", props=""):
    return ('<Item class="%s" referent="%s"><Properties><string name="Name">%s</string>%s</Properties>%s</Item>'
            % (cls, ref(), cls, props, children))

xml = ('<roblox xmlns:xmime="http://www.w3.org/2005/05/xmlmime" '
       'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
       'xsi:noNamespaceSchemaLocation="http://www.roblox.com/roblox.xsd" version="4">'
       '<Meta name="ExplicitAutoJoints">true</Meta>'
       + service("Workspace")
       + service("Lighting")
       + service("ReplicatedStorage")
       + service("ServerScriptService", script("Script", "QuikStopServer", server))
       + service("StarterPlayer", service("StarterPlayerScripts", script("LocalScript", "QuikStopClient", client)))
       + '</roblox>')
out = root / "QuikStop.rbxlx"
out.write_text(xml)
print("wrote", out, len(xml), "bytes")
