module = angular.module 'docflow.user.login', ['docflow.ui.utils']

module.config(
  ['$httpProvider',
  (($httpProvider) ->
    $httpProvider.responseInterceptors.push 'userLoginHttpListener' # listener is defined right below
    return)
  ])

module.run(
  ['$docflowUtils', '$rootScope',
  (($docflowUtils, $rootScope) ->
    $rootScope.signOut = (->
      $http(
        method: 'POST'
        url: '/signout')
      .then (resp) ->
        window.location = "/signin?url=#{$docflowUtils.encodeUriQuery($location.url(), true)}"
      return)
    return)]
  )

module.factory 'userLoginHttpListener',
  ['$location', '$docflowConfig', '$docflowUtils', '$q', '$log',
  (($location, $docflowConfig, $docflowUtils, $q, $log) ->
    return ((promise) -> promise.then(
      ((resp) -> # ok
        if resp.data?.code == "NotAuthenticated"
          # regirect to login page
          # TODO: At the moment parameter 'url' becomes dirty - fix whole story
          window.location = "/signin?url=#{$docflowUtils.encodeUriQuery($location.url(), true)}&#{$docflowConfig.tmplParams}"
          return $q.reject(resp)
        return resp
      ), ((resp) -> # failed
        $log.error resp
        return $q.reject(resp)
      )))
  )]
