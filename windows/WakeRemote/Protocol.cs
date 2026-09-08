using System.Security.Cryptography;
using System.Text;

namespace WakeRemote;

public sealed record SignedRequest(string Body, string Timestamp, string Nonce, string Canonical, string Signature);

public static class Protocol
{
    public const string ApiPrefix = "/api";

    /// <summary>
    /// Signed into the HMAC canonical string. Must stay identical to the server and every other client.
    /// </summary>
    public const string WakePath = ApiPrefix + "/v1/wake";

    public const string EnrollPath = ApiPrefix + "/v1/enroll";

    public static SignedRequest Sign(byte[] key, string target, long? timestamp = null, byte[]? nonce = null)
    {
        var body = $"{{\"target\":\"{target}\"}}";
        var stamp = (timestamp ?? DateTimeOffset.UtcNow.ToUnixTimeSeconds()).ToString();
        nonce ??= RandomNumberGenerator.GetBytes(16);
        var nonceHex = Convert.ToHexString(nonce).ToLowerInvariant();
        var hash = Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(body))).ToLowerInvariant();

        var canonical = string.Join("\n", "POST", WakePath, stamp, nonceHex, hash);
        using var hmac = new HMACSHA256(key);
        var signature = Convert.ToHexString(hmac.ComputeHash(Encoding.UTF8.GetBytes(canonical))).ToLowerInvariant();

        return new(body, stamp, nonceHex, canonical, signature);
    }
}
