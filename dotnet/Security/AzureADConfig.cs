namespace dotnet.Security {

    public class AzureADConfig {
        private string clientId;
        public string ClientId
        {
            get { return clientId; }
            set { clientId = value; }
        }
        
        private string tenantId;
        public string TenantId
        {
            get { return tenantId; }
            set { tenantId = value; }
        }
        
        private string appIdUri;
        public string AppIdUri
        {
            get { return appIdUri; }
            set { appIdUri = value; }
        }
        
    }
}