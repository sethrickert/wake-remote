using System;
using System.IO;
using System.Text.Json;
using WakeRemote;
using Xunit;

namespace WakeRemote.Tests;

public class ProtocolTests
{
    static JsonElement Vector()
    {
        var path = Path.Combine(AppContext.BaseDirectory, "test-vectors.json");
        if (!File.Exists(path))
        {
            // Walk up from the test output directory to the repo root. Doing this by search
            // rather than a fixed number of "../" hops keeps it working if an RID subfolder
            // is ever added to the test project's output path.
            for (var dir = new DirectoryInfo(AppContext.BaseDirectory); dir is not null; dir = dir.Parent)
            {
                var candidate = Path.Combine(dir.FullName, "shared", "test-vectors.json");
                if (File.Exists(candidate)) { path = candidate; break; }
            }
        }

        var json = JsonDocument.Parse(File.ReadAllText(path));
        return json.RootElement.GetProperty("vectors")[0].Clone();
    }

    [Fact]
    public void MatchesFixture()
    {
        var v = Vector();
        var key = Convert.FromHexString(v.GetProperty("key_hex").GetString()!);
        var nonce = Convert.FromHexString(v.GetProperty("nonce").GetString()!);
        var target = v.GetProperty("target").GetString()!;

        var signed = Protocol.Sign(key, target, v.GetProperty("timestamp").GetInt64(), nonce);

        Assert.Equal(v.GetProperty("body").GetString(), signed.Body);
        Assert.Equal(v.GetProperty("canonical").GetString(), signed.Canonical);
        Assert.Equal(v.GetProperty("signature").GetString(), signed.Signature);
    }

    [Fact]
    public void SignedPathIsTheApiPath()
    {
        Assert.Equal(Vector().GetProperty("path").GetString(), Protocol.WakePath);
        Assert.Equal(Protocol.WakePath, "/api/v1/wake");
    }
}
