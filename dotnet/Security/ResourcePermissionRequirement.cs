using Microsoft.AspNetCore.Authorization;

namespace dotnet.Security
{
    public class ResourcePermissionRequirement : IAuthorizationRequirement
    {
        private string _requiredRole;
        public string RequiredRole {
            get { return _requiredRole;}
        }
        public ResourcePermissionRequirement(string requiredRole) {
            _requiredRole = requiredRole;
        }
    }
}