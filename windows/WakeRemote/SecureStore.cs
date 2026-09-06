using System.Security.Cryptography;
using System.Text.Json;
using System.IO;
namespace WakeRemote;
public sealed record Target(string Alias,string Label);
public sealed record Enrollment(string KeyId,string ServerUrl,List<Target> Targets);
public static class SecureStore {
    static readonly string Dir=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"ApexTechLabs","WakeRemote");
    static readonly string Meta=Path.Combine(Dir,"enrollment.json"), Key=Path.Combine(Dir,"key.bin");
    public static bool Exists=>File.Exists(Meta)&&File.Exists(Key);
    public static void Save(Enrollment enrollment,byte[] key){Directory.CreateDirectory(Dir);File.WriteAllText(Meta,JsonSerializer.Serialize(enrollment));File.WriteAllBytes(Key,ProtectedData.Protect(key,null,DataProtectionScope.CurrentUser));}
    public static (Enrollment enrollment,byte[] key) Load(){var enrollment=JsonSerializer.Deserialize<Enrollment>(File.ReadAllText(Meta))??throw new InvalidDataException();return(enrollment,ProtectedData.Unprotect(File.ReadAllBytes(Key),null,DataProtectionScope.CurrentUser));}
    public static void Clear(){if(File.Exists(Key))File.Delete(Key);if(File.Exists(Meta))File.Delete(Meta);}
}
