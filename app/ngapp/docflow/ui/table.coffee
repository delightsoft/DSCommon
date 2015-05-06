module = angular.module 'docflow.ui.table', [

  'ui.bootstrap.pagination'

  'docflow.ui.actions'
  'docflow.ui.utils'
]

module.constant 'docflowPagingSize', 7

getQueryFromUrl = ((locationSearch, urlPrefix) ->
  query = {}
  for k, v of locationSearch
    if (k.substring(0, urlPrefix.length) == urlPrefix)
      query[k.substring(urlPrefix.length)] = decodeURI(v)
  return query)

putQueryToUrl = (($docflowUtils, locationSearch, query, urlPrefix) ->
  for k, v of query
    if k != 'c'
      n = urlPrefix + k
      if angular.isObject v
        locationSearch[n] = v.id
      else if v && !(k == 'p' && v == 1)
        locationSearch[n] = v
      else
        delete locationSearch[n]
  return locationSearch)

getList = (($scope, $http, $docflowActions, $docflowUtils, $docflowConfig, $q, docType, pagingSize, params, query, changes) ->
  args = new Array

  for p, v of params
    args.push "#{p}=#{$docflowUtils.encodeUriQuery(v, true)}" if angular.isDefined v

  for k of query
    if not (k == 'c' || params?.hasOwnProperty(k))
      v = if changes && changes.hasOwnProperty(k) then changes[k] else query[k]
      if angular.isDefined(v) && v != null
        if angular.isObject(v)
          args.push "#{k}=#{$docflowUtils.encodeUriQuery(v.id, true)}"
        else
          args.push "#{k}=#{$docflowUtils.encodeUriQuery(v, true)}"

  page = changes?['p'] || query['p'] || 1
  if page >= 0
    c = page + Math.round(pagingSize / 2)
    if c <= pagingSize then c = pagingSize + 1
    args.push "c=#{c}"

  if $docflowActions
    deferred = $q.defer()
    $docflowActions.send new $docflowActions.Action(docType, 'list', deferred.promise)  # it's a special built-in action

  return $http(
    method: 'GET'
    url: "/api/list/#{docType}?#{args.join '&'}&#{$docflowConfig.apiParams}")
  .then ((resp) ->
    if deferred
      if !resp.data.hasOwnProperty('code') || resp.data.code == 'Ok'
        deferred.resolve resp.data.message
      else
        deferred.reject resp.data.message
    $docflowUtils.processDocumentsListBeforeEditing resp.data.list
    return resp.data)
  )

module.directive 'docflowTable',
  ['$http', '$docflowActions', '$docflowUtils', '$docflowConfig', '$q', '$location', 'docflowPagingSize', '$log',
  (($http, $docflowActions, $docflowUtils, $docflowConfig, $q, $location, docflowPagingSize, $log) ->
    restrict: 'E'
    replace: false
    transclude: true
    scope: true
    controller: ['$scope', '$attrs', (($scope, $attrs) -> # controller is required, to initialize query prior ng-inits in the template
      $scope.urlPrefix = $attrs.urlPrefix
      $scope.query = if $scope.urlPrefix then getQueryFromUrl($location.search(), $scope.urlPrefix) else $location.search()
      $scope.docType = $attrs.docType
      $scope.pagingSize = $attrs.pagingSize || docflowPagingSize
      $scope.params = {}
      $scope.ctrlValues = {}
      $scope.values = {}
      $scope.items = null
      $scope.$watch 'query', ((n, o) ->
        if n != o
          angular.copy n, $scope.values
        return), true
      return)]

    link: (($scope, element, attrs, controller, $transclude) ->

      if !$transclude
        $log.error 'Missing transclution content'

      if attrs.data
        value = $scope.$eval attrs.data
        if not angular.isObject value
          throw new Error 'Invalid docflow-table data: #{attrs.data}'
        angular.extend $scope, value
        if attrs.onLoad
          $scope.$eval attrs.onLoad,
            'list': $scope.list
      else if attrs.params # params specified as directive attribute. wait for $observe
        value = $scope.$eval attrs.params
        if not angular.isObject value
          throw new Error 'Invalid docflow-table params: #{attrs.params}'
        $scope.params = value
        $scope.$evalAsync 'update()'
      else # if no params and no data attributes, update right away
        $scope.$evalAsync 'update()'

      $scope.update = ((changes) ->
        getList($scope, $http, (if $scope.urlPrefix then null else $docflowActions), $docflowUtils, $docflowConfig, $q,
          $scope.docType, $scope.pagingSize, $scope.params, $scope.query, changes)
        .then (update) ->
          $scope.query = query = update.query
          $scope.list = list = update.list
          $scope.items = if query.p == query.c then (query.p - 1) * query.s + list?.length else null # Calculate items for last page

          if attrs.onLoad
            $scope.$eval attrs.onLoad,
              'list': $scope.list
          $location.search putQueryToUrl $docflowUtils, $location.search(), $scope.query, $scope.urlPrefix
          $location.replace()
        return)

      $scope.onSelectPage = ((page) ->
        if page == $scope.query.c # c - checkToPage
          $scope.update p: -1
        else
          $scope.update p: page
        return)

      $transclude $scope, (clone) ->
        element.empty()
        element.append clone

      return)
  )]
