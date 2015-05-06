module = angular.module 'docflow.widget.selector', [
  'docflow.config'
  'docflow.widget.select2'
]

module.directive 'docflowWidgetSelector', [
  '$docflow', '$docflowConfig', '$http', '$timeout', '$log',
  (($docflow, $docflowConfig, $http, $timeout, $log) ->
    docType = 'Position'
    return ({
    scope: true
    restrict: 'A'
    require: 'ngModel'
    link: ($scope, element, attrs, ngModel) ->

      params = {}

      if attrs.docflowWidgetSelector
        angular.extend params, $scope.$eval attrs.docflowWidgetSelector

      if !params.docType
        $log.error 'Missing \'type\' attribute'
        return

      if !params.template
        $log.error 'Missing \'template\' attribute'
        return

      $scope.createNew = ->
        $docflow.createDocDialog(params.docType, resultTemplate: 'dict')
        .then (value) ->
          if (value)
            ngModel.$setViewValue value
            ngModel.$render()
          return
        return

      ie = element.find('[docflow-select2]')
      input = if ie.length > 0 then $(ie[0]) else element # defauls: element itself
      url = "/api/list/#{params.docType}?#{$docflowConfig.apiParams}"
      if(angular.isDefined(params.filter) && params.filter.length>0)
        url = "/api/list/#{params.docType}?#{$docflowConfig.apiParams}" + "&#{params.filter}"

      options =
        cache: false
        url: url
        dateType: 'json'
        quiteMillis: 300

        data: ((term, page) ->
          res =
            t: params.template
          if term then res.x = term
          if page
            res.p = page
            res.c = page + 1
          return res)

        results: ((data, page) ->
          res = (->
            if data.list != null
              if options.text
                for p in data.list
                  p.text = p[options.text]
                  delete p[options.text]
              return {
                results: data.list
                more: data.query.c > data.query.p }
            return results: []
          )()
          if page == 1 && angular.isDefined(options.first)
            v =
              id: ''
              text: options.first
            if res.results
              res.results.unshift v
            else
              res.results = [v]
          return res)

      angular.extend options, params

      select2Init = false

      $timeout ->
        input.select2 ajax: options
        if options.readonly
          input.select2 'readonly', true
        select2Init = true
        ngModel.$render()

      input.change ->
        v = input.select2 'data'
        ngModel.$setViewValue if v.id then v else null
        $scope.$apply()
        return

      ngModel.$render = ->
        v = ngModel.$viewValue
        if select2Init
          if !angular.isObject v
            v =
              if !v
                id: ''
                text: options.first
              else
                id: v
                text: ''
          input.select2 'data', v
          return
        input.val null
        return

      return # End of link:
    })
  )]
