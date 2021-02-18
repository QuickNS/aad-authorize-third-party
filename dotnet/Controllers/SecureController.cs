using System;
using System.Collections.Generic;
using System.IdentityModel.Tokens.Jwt;
using System.Linq;
using System.Threading.Tasks;
using dotnet.Security;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using Microsoft.Extensions.Primitives;
using Microsoft.Identity.Web.Resource;

namespace dotnet.Controllers
{
    [ApiController]
    public class SecureController : ControllerBase
    {
        private readonly IAuthorizationService _authorizationService;
        private readonly AzureADConfig _azureADConfig;
        private readonly ILogger<SecureController> _logger;

        public SecureController(IAuthorizationService authorizationService,
                                IOptions<AzureADConfig> azureAdConfig,
                                ILogger<SecureController> logger)
        {
            _authorizationService = authorizationService;
            _azureADConfig = azureAdConfig.Value;
            _logger = logger;
        }


        [HttpGet]
        [Authorize(Policy = "ReadOnlyResourceAccess")]
        [Route("/api/{clientId}/data")]
        public IActionResult ReadData(string clientId)
        {
            return Ok($"Get Request Validated with ReadOnlyResourceAccess Policy\nShowing data for customer: {clientId}");
        }

        [HttpPut]
        [Authorize(Policy = "WriteResourceAccess")]
        [Route("/api/{clientId}/data")]
        public IActionResult WriteData(string clientId)
        {
            return Ok($"Put Request Validated with WriteResourceAccess Policy\nShowing data for customer: {clientId}");
        }

        [HttpGet]
        [Authorize(Policy = "ReadOnlyResourceAccess")]
        [Route("/api/{clientId}/debug")]
        public IActionResult Debug(string clientId)
        {
            var authContext = TokenValidator.DoTokenValidationsForClientId(_azureADConfig, HttpContext, clientId, "API.Read");
            return Ok(authContext);
        }

    }
}
