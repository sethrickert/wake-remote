using System.Net;
using System.Net.Http;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
namespace WakeRemote;
public sealed record WakeResult(bool Success,string Title,string Detail,int RetryAfter=0);
public static class WakeClient {
    static readonly HttpClient Http=new(){Timeout=TimeSpan.FromSeconds(8)};
    public static async Task<(Enrollment enrollment,byte[] key)> EnrollAsync(string uri,bool allowHttp=false){
        if(!Uri.TryCreate(uri,UriKind.Absolute,out var link)||link.Scheme!="wakeremote"||link.Host!="enroll")throw new ArgumentException("Paste a valid Wake Remote enrollment link.");
        var query=System.Web.HttpUtility.ParseQueryString(link.Query);if(query["v"]!="1"||string.IsNullOrWhiteSpace(query["t"]))throw new ArgumentException("The enrollment link is malformed.");
        if(!Uri.TryCreate(query["url"],UriKind.Absolute,out var server)||server.Scheme!="https"&&!(allowHttp&&server.Scheme=="http"))throw new ArgumentException("Enrollment requires HTTPS unless LAN HTTP is explicitly enabled.");
        using var response=await Http.PostAsJsonAsync(new Uri(server,Protocol.EnrollPath),new{token=query["t"]});if(!response.IsSuccessStatusCode)throw new InvalidOperationException(response.StatusCode==HttpStatusCode.Unauthorized?"This enrollment link is expired or already used.":$"Enrollment failed ({(int)response.StatusCode}).");
        using var json=JsonDocument.Parse(await response.Content.ReadAsStringAsync());var root=json.RootElement;var keyHex=root.GetProperty("key").GetString()!;var targets=root.GetProperty("targets").EnumerateArray().Select(t=>new Target(t.GetProperty("alias").GetString()!,t.GetProperty("label").GetString()!)).ToList();
        return(new Enrollment(root.GetProperty("key_id").GetString()!,root.GetProperty("server_url").GetString()!,targets),Convert.FromHexString(keyHex));
    }
    public static async Task<WakeResult> WakeAsync(Enrollment enrollment,byte[] key,string target){
        var signed=Protocol.Sign(key,target);using var request=new HttpRequestMessage(HttpMethod.Post,enrollment.ServerUrl.TrimEnd('/')+Protocol.WakePath){Content=new StringContent(signed.Body,Encoding.UTF8,"application/json")};request.Headers.Add("X-Key-Id",enrollment.KeyId);request.Headers.Add("X-Timestamp",signed.Timestamp);request.Headers.Add("X-Nonce",signed.Nonce);request.Headers.Add("X-Signature",signed.Signature);
        try{using var response=await Http.SendAsync(request);return response.StatusCode switch{HttpStatusCode.NoContent=>new(true,"Wake signal sent","Your computer may take a moment to come online."),HttpStatusCode.Unauthorized=>new(false,"Authentication failed","Check the enrolled key and automatic date and time."),HttpStatusCode.NotFound=>new(false,"Configuration error","The selected target is not configured."),(HttpStatusCode)429=>new(false,"Too many attempts","Wait a minute before trying again.",response.Headers.RetryAfter?.Delta is {} d?(int)d.TotalSeconds:60),HttpStatusCode.ServiceUnavailable=>new(false,"Target network unavailable","The server could not reach the target network."),_=>new(false,"Request not sent",$"Unexpected server response ({(int)response.StatusCode}).")};}catch(TaskCanceledException){return new(false,"Connection unavailable","The server did not respond in time.");}catch(HttpRequestException){return new(false,"Connection unavailable","Check your network connection and try again.");}
    }
}
