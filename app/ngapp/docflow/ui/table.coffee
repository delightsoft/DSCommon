module = angular.module 'docflow.ui.table', ['docflow.ui.utils']

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
        locationSearch[n] = $docflowUtils.encodeUriQuery v.id, true
      else if v
        locationSearch[n] = $docflowUtils.encodeUriQuery v, true
      else
        delete locationSearch[n]
  return locationSearch)

getList = (($http, $docflowUtils, $docflowConfig, docType, pagingSize, params, query, changes) ->
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
  return $http(
    method: 'GET'
    url: "/api/list/#{docType}?#{args.join '&'}&#{$docflowConfig.apiParams}")
  .then ((resp) ->
    $docflowUtils.processDocumentsListBeforeEditing resp.data.list
    return resp.data)
  )

module.factory '$docflowTable',
  ['$http', '$docflowUtils', '$docflowConfig', '$location', 'docflowPagingSize',
  (($http, $docflowUtils, $docflowConfig, $location, docflowPagingSize) ->
    getData: (docType, params, urlPrefix) ->
      query = if urlPrefix then getQueryFromUrl($location.search(), urlPrefix) else {}
      return getList($http, $docflowUtils, $docflowConfig, docType, docflowPagingSize, params, query)
      .then (data) ->
        data.docType = docType
        data.urlPrefix = urlPrefix
        data.params = params if params
        data.pagingSize = docflowPagingSize
        return data
  )]
# End of factory '$docflowTable'

module.directive 'docflowTable',
  ['$http', '$docflowUtils', '$docflowConfig', '$location', 'docflowPagingSize',
  (($http, $docflowUtils, $docflowConfig, $location, docflowPagingSize) ->
    sa = null
    return { # link:

      template: '<div ng-transclude></div>'
      restrict: 'E'
      replace: false
      transclude: true

      controller: (($scope) -> # controller is required, to initialize query prior ng-inits in the template
        $scope.urlPrefix = sa.urlPrefix
        $scope.query = if $scope.urlPrefix then getQueryFromUrl($location.search(), $scope.urlPrefix) else {}
        $scope.docType = sa.docType
        $scope.pagingSize = sa.pagingSize || docflowPagingSize
        $scope.params = {}
        $scope.ctrlValues = {}
        delete sa
        $scope.values
        $scope.$watch 'query', ((n, o) ->
          if n != o
            angular.copy n, $scope.values
          return), true
        return)

      compile: (element, attrs) ->
        sa = attrs
        return (($scope, element, attrs) ->
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
            getList($http, $docflowUtils, $docflowConfig, $scope.docType, $scope.pagingSize, $scope.params, $scope.query, changes)
            .then (update) ->
              $scope.query = update.query
              $scope.list = update.list
              if attrs.onLoad
                $scope.$eval attrs.onLoad,
                  'list': $scope.list
              $location.search putQueryToUrl $docflowUtils, $location.search(), $scope.query, $scope.urlPrefix if $scope.urlPrefix
              $location.replace()
            return)

          $scope.onSelectPage = ((page) ->
            if page == $scope.query.c # c - checkToPage
              $scope.update p: -1
            else
              $scope.update p: page
            return)

          return)
    }
  )]
