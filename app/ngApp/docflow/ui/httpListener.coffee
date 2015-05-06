module = angular.module 'docflow.ui.httpListener', ['docflow.config']

# TODO: React on user and app version change by restarting UI
# TODO: Consider maintanence warning to the users, to smoothly update versions

module.config ['$httpProvider', (($httpProvider) ->
  $httpProvider.responseInterceptors.push 'appHttpListener'
  return
)]

module.factory 'appHttpListener',
  ['$q', '$log', '$location', '$docflowConfig',
  (($q, $log, $location, $docflowConfig) ->
    (promise) -> promise.then(
      ((resp) -> # ok
        return resp),
      ((resp) -> # failed
        if resp.status == 401 # Unauthorized
          $docflowConfig.goToLogin?()
          return $q.reject(resp)
        $log.error resp
        return $q.reject(resp)
      ))
  )]

#For Angular 1.4
#
#module.config ['$httpProvider', (($httpProvider) ->
#  $httpProvider.interceptors.push 'appHttpListener'
#  return
#)]
#
#module.factory 'appHttpListener',
#  ['$q', '$log', '$location', '$docflowConfig',
#   (($q, $log, $location, $docflowConfig) ->
#     return {
#     response: ((resp) ->
#       return resp)
#     responseError: ((resp) ->
#       if resp.status == 401 # Unauthorized
#         $docflowConfig.goToLogin?()
#         return $q.reject(resp)
#       $log.error resp
#       return $q.reject(resp))}
#   )]
