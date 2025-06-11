using System.Reflection;

class Program
{
    static void Main(string[] args)
    {
        Thread trayThread = new Thread(new ThreadStart(RunTrayApp));
        trayThread.SetApartmentState(ApartmentState.STA);
        trayThread.Start();
        trayThread.Join();

        NativeDllLoader.LoadNativeSDKs();
    }

    private static void RunTrayApp()
    {
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Application.Run(new MyAppContext());
    }
}

public class MyAppContext : ApplicationContext
{
    private NotifyIcon trayIcon;

    public MyAppContext()
    {
        trayIcon = new NotifyIcon()
        {
            Icon = new Icon(Assembly.GetExecutingAssembly().GetManifestResourceStream("MineLightsV2.resources.app_icon.ico")),
            Text = "MineLights Server",
            ContextMenuStrip = new ContextMenuStrip(),
            Visible = true
        };

        trayIcon.ContextMenuStrip.Items.Add("Show Version", null, OnShowVersion);
        trayIcon.ContextMenuStrip.Items.Add("Exit", null, OnExit);
    }

    void OnShowVersion(object sender, EventArgs e)
    {
        MessageBox.Show("MineLights Server V2");
    }

    void OnExit(object sender, EventArgs e)
    {
        trayIcon.Visible = false;
        Application.Exit();
    }
}