module = angular.module 'docflow.widget.tags', ['docflow.config']

module.directive 'docflowWidgetTags', [
  '$docflow', '$docflowConfig', '$http', '$timeout', '$log',
  (($docflow, $docflowConfig, $http, $timeout, $log) ->
    scope: true
    restrict: 'A'
    require: 'ngModel'
    link: (($scope, element, attrs, ngModel) ->

      params = {}

      if attrs.docflowWidgetTags
        angular.extend params, $scope.$eval attrs.docflowWidgetTags

      if !params.docType
        $log.error 'Missing \'docType\' attribute'
        return

      if !params.template
        $log.error 'Missing \'template\' attribute'
        return

      $scope.createNew = ->
        $docflow.dialogCreate(params.docType, 'item', 'dict')
        .then (value) ->
          if (value)
            ngModel.$setViewValue value
            ngModel.$render()
          return
        return

      ie = element.find('[docflow-select2]')
      input = if ie.length > 0 then $(ie[0]) else element # defauls: element itself

      options =
        tags: null
        ajax:
          cache: false
          url: "/api/list/#{params.docType}?&#{$docflowConfig.apiParams}"
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
              data.list = data.list || []
              list = data.list
              if options.text
                for p in list
                  p.text = p[options.text]
                  delete p[options.text]
              if data.query.x
                term = data.query.x.toUpperCase()
                for p, i in list
                  if p.text.toUpperCase() == term
                    break;
                if i == list.length
                  list.unshift
                    id: '_' + term
                    text: data.query.x
              return {
                results: data.list
                more: data.query.c > data.query.p })()
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
        input.select2 options
# TODO: Fix options in selector
#        if options.readonly
#          input.select2 'readonly', true
        select2Init = true
        ngModel.$render()

      input.change ->
        v = input.select2 'data'
        tags = []
        for tag in v
          if tag.id.substr(0, 1) == '_' # it's new tag
            tags.push
              _u:
                tag:
                  id: params.docType
#                  text: tag.text
                  title: tag.text
          else
            tags.push
              _u:
                tag: tag.id
        ngModel.$setViewValue tags
        $scope.$apply()
        return

      ngModel.$render = (->
        v = ngModel.$viewValue
        if select2Init
          if v
            if !angular.isArray v
              $log.error 'Expected list of tags'
              return
            list = []
            for record in v
              list.push record.tag if record.tag
            input.select2 'data', list
          else
            input.select2 'data', null
        return)
      return)
  )]
