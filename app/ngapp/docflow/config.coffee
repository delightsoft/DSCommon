module = angular.module 'docflow.config', ['docflow.ui.utils', 'ng']

# helper method
prop = ((obj, prop, func, configurable) ->
  Object.defineProperty obj, prop,
    configurable: false
    get: func
  return)
# helper method
oneTimeCalculatedProp = ((obj, prop, func, configurable) ->
  Object.defineProperty obj, prop,
    configurable: true
    get: (->
      v = func()
      Object.defineProperty obj, prop,
        configurable: !!configurable
        value: v
      return v)
  return)

module.provider '$docflowConfig',
  ['$docflowUtilsProvider', '$windowProvider', '$injector',
  (($docflowUtilsProvider, $windowProvider, $injector) ->
    # this provider property allows to redefine config in specs
    @docflowConfig = $windowProvider.$get().docflowConfig
    # current user object to be displayed on interface
    oneTimeCalculatedProp @, 'user', (=> return @docflowConfig.user)
    # collection of document configurations tunned to specific user rights
    # Note: In config phase 'docs' remains unprocessed!
    oneTimeCalculatedProp @, 'docs', (=>
      # initial preprocessing required
      for docName, docConfig of @docflowConfig.docs
        if docConfig.$n
          $docflowUtilsProvider.processDocumentBeforeEditing docConfig.$n
        for actionName, actionConfig of docConfig.actions
          if actionConfig.params
            actionConfig.params =
              _u: actionConfig.params
      return @docflowConfig.docs)
    # collection of localized messages
    oneTimeCalculatedProp @, 'messages', (=>
      return @docflowConfig.messages || {})
    @$get = ['$location', (($location) =>
      # value of 'debug' parameter from $location. get's initialized in module.run(...)
      prop @, 'debug', (->
        return $location.search().debug)
      prop @, 'user', (->
        return $location.search().user)
      # parameters that should be added to each angular template call to the server
      prop @, 'tmplParams', (->
        params = $location.search()
        res = []
        if debug = params.debug then res.push "debug=#{debug}"
        if user = params.user then res.push "user=#{user}"
        return res.join '&')
      # parameters that should be added to each DSCommon API call
      prop @, 'apiParams', (->
        return if val = $location.search().user then "user=#{val}" else '')
      return @)]
    return)]

module.run(
  ['$docflowConfig', '$rootScope', '$location',
  (($docflowConfig, $rootScope, $location) ->
    res = {}
    if $docflowConfig.debug
      res.debug = $docflowConfig.debug
    if $docflowConfig.user
      res.user = $docflowConfig.user
    $rootScope.$on '$stateChangeSuccess', (->
      $location.search angular.extend $location.search(), res
      $location.replace()
      return)
    return)]
  )