(function() {
  var module;

  module = angular.module('docflow.ui.httpListener', ['docflow.config']);

  module.config([
    '$httpProvider', (function($httpProvider) {
      $httpProvider.responseInterceptors.push('appHttpListener');
    })
  ]);

  module.factory('appHttpListener', [
    '$q', '$log', '$location', '$docflowConfig', (function($q, $log, $location, $docflowConfig) {
      return function(promise) {
        return promise.then((function(resp) {
          return resp;
        }), (function(resp) {
          if (resp.status === 401) {
            if (typeof $docflowConfig.goToLogin === "function") {
              $docflowConfig.goToLogin();
            }
            return $q.reject(resp);
          }
          $log.error(resp);
          return $q.reject(resp);
        }));
      };
    })
  ]);

}).call(this);
