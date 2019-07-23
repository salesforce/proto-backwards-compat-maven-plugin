package main

import (
    "github.com/nilslice/protolock"
	"github.com/nilslice/protolock/extend"
)

func main() {
	plugin := extend.NewPlugin("sample") // "sample" is arbitrary name used to correlate error messages
	plugin.Init(func(data *extend.Data) *extend.Data {
		warnings := addWarningsForExample(data.Current, data.Updated)
		data.PluginWarnings = append(data.PluginWarnings, warnings...)
		return data
	})
}

func addWarningsForExample(cur, upd protolock.Protolock) []protolock.Warning {
	return []protolock.Warning{
		{
			Filepath: protolock.OSPath(upd.Definitions[0].Filepath),
			Message:  "A sample warning!",
		},
		{
			Filepath: protolock.OSPath(upd.Definitions[0].Filepath),
			Message:  "Another sample warning.. ah!",
		},
	}
}
