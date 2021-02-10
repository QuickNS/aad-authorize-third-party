using System.Collections.Generic;

namespace dotnet.Security
{
    public class AuthContext
    {
        public string Audience { get; set; }
        public string RequesterId { get; set; }
        public string RequiredRole { get; set; }
        public bool IsThirdPartyAccessAttempt { get; set; }
        public List<string> Roles { get; set; }
        public string RequestedClientId { get; set; }
        public List<string> ClientRoles { get; set; }
        public string ApiClientId { get; set; }
        public string TenantId { get; set; }
        public bool Authorized { get; set; }
        public string ForbidReason { get; set; }
    }
}