using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Interop;
using System.Windows.Media;
using Forms = System.Windows.Forms;
using Application = System.Windows.Application;
using Button = System.Windows.Controls.Button;
using CheckBox = System.Windows.Controls.CheckBox;
using ComboBox = System.Windows.Controls.ComboBox;
using Image = System.Windows.Controls.Image;
using TextBox = System.Windows.Controls.TextBox;
using MessageBox = System.Windows.MessageBox;
using Brush = System.Windows.Media.Brush;
using Brushes = System.Windows.Media.Brushes;

namespace WakeRemote;

public partial class MainWindow : Window
{
    enum Screen { Home, Enroll, Settings }

    readonly Forms.NotifyIcon tray;
    Enrollment? enrollment;
    byte[]? key;
    ComboBox? targets;
    TextBlock? statusTitle, statusDetail;
    Border? statusCard;
    Screen screen = Screen.Home;

    static readonly Brush White = B("#FFFFFF"), Muted = B("#929292"), Blue = B("#26A7E1"),
                          Red = B("#DC2626"), Surface = B("#181818"), Outline = B("#262626");

    static Brush B(string c) =>
        new BrushConverter().ConvertFromString(c) as Brush ?? Brushes.White;

    public MainWindow()
    {
        InitializeComponent();

        // Assets.TrayIcon falls back to a stock icon rather than throwing. This
        // constructor previously did `new System.Drawing.Icon("Assets/app.ico")`, a
        // working-directory-relative file read, which threw for any launch that did not
        // start in the install folder and left the app with no window and no tray icon.
        tray = new Forms.NotifyIcon
        {
            Icon = Assets.TrayIcon("app.ico"),
            Text = "Wake Remote",
            Visible = true,
        };

        var menu = new Forms.ContextMenuStrip();
        menu.Items.Add("Wake", null, (_, _) => Dispatcher.Invoke(() => _ = Wake()));
        menu.Items.Add("Open", null, (_, _) => Dispatcher.Invoke(() =>
        {
            Show();
            WindowState = WindowState.Normal;
            Activate();
        }));
        menu.Items.Add("Exit", null, (_, _) => Dispatcher.Invoke(() =>
        {
            tray.Visible = false;
            Application.Current.Shutdown();
        }));
        tray.ContextMenuStrip = menu;
        tray.DoubleClick += (_, _) => Dispatcher.Invoke(() => { Show(); Activate(); });

        Closing += (_, e) => { e.Cancel = true; Hide(); };

        LoadEnrollment();
        ShowHome();
    }

    void LoadEnrollment()
    {
        try
        {
            if (SecureStore.Exists) (enrollment, key) = SecureStore.Load();
        }
        catch
        {
            SecureStore.Clear();
            enrollment = null;
            key = null;
        }
    }

    StackPanel Base()
    {
        Body.Children.Clear();
        statusTitle = statusDetail = null;
        statusCard = null;
        var panel = new StackPanel();
        Body.Children.Add(panel);
        return panel;
    }

    static TextBlock Text(string value, double size, Brush brush, FontWeight? weight = null) => new()
    {
        Text = value,
        FontSize = size,
        Foreground = brush,
        FontWeight = weight ?? FontWeights.Normal,
        TextWrapping = TextWrapping.Wrap,
        TextAlignment = TextAlignment.Center,
        Margin = new Thickness(0, 0, 0, 8),
    };

    Button Primary(string label) => new()
    {
        Content = label,
        Style = (Style)FindResource("PrimaryButton"),
        Margin = new Thickness(0, 8, 0, 0),
    };

    static Image? Logo(double size, Thickness margin)
    {
        var source = Assets.Bitmap("icon.png");
        if (source is null) return null;   // art is decoration; never block the screen on it
        return new Image { Source = source, Width = size, Height = size, Stretch = Stretch.Uniform, Margin = margin };
    }

    Border Status(string title, string detail, Brush? accent = null)
    {
        statusTitle = Text(title, 17, White, FontWeights.Bold);
        statusTitle.TextAlignment = TextAlignment.Left;
        statusDetail = Text(detail, 14, Muted);
        statusDetail.TextAlignment = TextAlignment.Left;

        var stack = new StackPanel { Margin = new Thickness(18) };
        stack.Children.Add(statusTitle);
        stack.Children.Add(statusDetail);

        statusCard = new Border
        {
            MinHeight = 104,
            Background = Surface,
            BorderBrush = accent ?? Outline,
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(16),
            Child = stack,
            Margin = new Thickness(0, 8, 0, 8),
        };
        return statusCard;
    }

    void SetStatus(string title, string detail, Brush? accent = null)
    {
        if (statusTitle is null || statusDetail is null) return;
        statusTitle.Text = title;
        statusDetail.Text = detail;
        if (statusCard is not null) statusCard.BorderBrush = accent ?? Outline;
    }

    void ShowHome()
    {
        screen = Screen.Home;
        BackButton.Visibility = Visibility.Hidden;
        SettingsButton.Visibility = Visibility.Visible;

        var p = Base();
        if (Logo(128, new Thickness(0, 0, 0, 16)) is { } logo) p.Children.Add(logo);
        p.Children.Add(Text("Wake your PC", 34, White, FontWeights.Bold));
        p.Children.Add(Text("Securely send a wake signal from anywhere.", 15, Muted));

        // Not enrolled is a not-ready state, so it reads red rather than brand blue.
        p.Children.Add(enrollment is null
            ? Status("Setup required", "Enroll this device to continue.", Red)
            : Status("Ready", "Secure key installed.", Blue));

        if (enrollment?.Targets.Count > 1)
        {
            targets = new ComboBox
            {
                ItemsSource = enrollment.Targets,
                DisplayMemberPath = "Label",
                SelectedIndex = 0,
                Height = 48,
                Margin = new Thickness(0, 0, 0, 8),
            };
            p.Children.Add(targets);
        }

        var button = Primary(enrollment is null ? "Set up secure key" : "Wake computer");
        if (enrollment is null) button.Click += (_, _) => ShowEnroll();
        else button.Click += async (_, _) => await Wake();
        p.Children.Add(button);
    }

    void ShowEnroll()
    {
        screen = Screen.Enroll;
        BackButton.Visibility = Visibility.Visible;
        SettingsButton.Visibility = Visibility.Hidden;

        var p = Base();
        if (Logo(128, new Thickness(0, 0, 0, 8)) is { } logo) p.Children.Add(logo);
        p.Children.Add(Text("Connect Wake Remote", 30, White, FontWeights.Bold));
        p.Children.Add(Text("Paste the single-use enrollment link, or enter the 64-character key manually.", 14, Muted));

        var input = new TextBox
        {
            ToolTip = "wakeremote://enroll?... or 64 hexadecimal characters",
            Margin = new Thickness(0, 16, 0, 8),
        };
        p.Children.Add(input);
        p.Children.Add(Text("Scan the server QR with any scanner, then paste the link here.", 12, Muted));
        p.Children.Add(Status("Secure enrollment", "Your key is protected with Windows DPAPI for this user."));

        var button = Primary("Enroll securely");
        button.Click += async (_, _) =>
        {
            try
            {
                button.IsEnabled = false;
                var value = input.Text.Trim();
                if (System.Text.RegularExpressions.Regex.IsMatch(value, "^[0-9a-fA-F]{64}$"))
                {
                    // Manual hex only. A link or QR carries the real server and targets,
                    // so this placeholder is never used for those paths.
                    var manual = new Enrollment("main", "https://wol.apextechlabs.com", new() { new("main-pc", "Main PC") });
                    SecureStore.Save(manual, Convert.FromHexString(value));
                }
                else
                {
                    var result = await WakeClient.EnrollAsync(value);
                    SecureStore.Save(result.enrollment, result.key);
                }
                LoadEnrollment();
                ShowHome();
            }
            catch (Exception ex)
            {
                SetStatus("Enrollment failed", ex.Message, Red);
                button.IsEnabled = true;
            }
        };
        p.Children.Add(button);
    }

    void ShowSettings()
    {
        screen = Screen.Settings;
        BackButton.Visibility = Visibility.Visible;
        SettingsButton.Visibility = Visibility.Hidden;

        var p = Base();
        p.Children.Add(Text("Settings", 34, White, FontWeights.Bold));
        p.Children.Add(Text(enrollment is null
            ? "Not enrolled"
            : $"Service: {enrollment.ServerUrl}\nTarget: {string.Join(", ", enrollment.Targets.Select(t => t.Label))}",
            14, Muted));

        var launch = new CheckBox
        {
            Content = "Launch at login",
            Foreground = White,
            Margin = new Thickness(0, 24, 0, 8),
            IsChecked = LoginStartup.Enabled,
        };
        launch.Checked += (_, _) => LoginStartup.Enabled = true;
        launch.Unchecked += (_, _) => LoginStartup.Enabled = false;
        p.Children.Add(launch);
        p.Children.Add(Text("Global hotkey: Ctrl+Alt+W", 13, Muted));

        var remove = new Button
        {
            Content = "Remove secure key",
            Height = 48,
            Background = B("#7B2929"),
            Foreground = White,
            BorderThickness = new Thickness(0),
            Margin = new Thickness(0, 32, 0, 0),
        };
        remove.Click += (_, _) =>
        {
            if (MessageBox.Show("Remove this user’s secure enrollment?", "Wake Remote",
                    MessageBoxButton.YesNo, MessageBoxImage.Warning) == MessageBoxResult.Yes)
            {
                SecureStore.Clear();
                LoadEnrollment();
                ShowHome();
            }
        };
        p.Children.Add(remove);
    }

    async Task Wake()
    {
        if (enrollment is null || key is null)
        {
            ShowEnroll();
            return;
        }

        // The tray menu and Ctrl+Alt+W can fire from any screen. Without this the status
        // fields still referenced TextBlocks from a screen that had been cleared, so the
        // text went to detached elements the user could not see.
        if (screen != Screen.Home) ShowHome();

        var alias = (targets?.SelectedItem as Target)?.Alias ?? enrollment.Targets[0].Alias;
        SetStatus("Sending securely", "Creating a fresh signed request…", Blue);

        var result = await WakeClient.WakeAsync(enrollment, key, alias);
        SetStatus(result.Title, result.Detail, result.Success ? Blue : Red);
        tray.ShowBalloonTip(3000, result.Title, result.Detail,
            result.Success ? Forms.ToolTipIcon.Info : Forms.ToolTipIcon.Warning);
    }

    void Back_Click(object s, RoutedEventArgs e) => ShowHome();

    void Settings_Click(object s, RoutedEventArgs e) => ShowSettings();

    protected override void OnSourceInitialized(EventArgs e)
    {
        base.OnSourceInitialized(e);
        var source = HwndSource.FromHwnd(new WindowInteropHelper(this).Handle);
        source.AddHook((IntPtr h, int m, IntPtr w, IntPtr l, ref bool handled) =>
        {
            if (m == 0x0312 && w.ToInt32() == 9001)   // WM_HOTKEY
            {
                _ = Wake();
                handled = true;
            }
            return IntPtr.Zero;
        });
        RegisterHotKey(source.Handle, 9001, 0x0002 | 0x0001, 0x57);   // MOD_CONTROL | MOD_ALT, W
    }

    [DllImport("user32.dll")]
    static extern bool RegisterHotKey(IntPtr hWnd, int id, uint fsModifiers, uint vk);
}

static class LoginStartup
{
    const string Path = @"Software\Microsoft\Windows\CurrentVersion\Run";

    public static bool Enabled
    {
        get
        {
            using var k = Microsoft.Win32.Registry.CurrentUser.OpenSubKey(Path);
            return k?.GetValue("WakeRemote") != null;
        }
        set
        {
            using var k = Microsoft.Win32.Registry.CurrentUser.CreateSubKey(Path);
            if (value) k.SetValue("WakeRemote", $"\"{Environment.ProcessPath}\"");
            else k.DeleteValue("WakeRemote", false);
        }
    }
}
