module = angular.module 'docflow.widget.subtable', ['docflow.config']

module.directive 'docflowHideValidation', [
  (() ->
    scope: true
    restrict: 'A'
    link: (($scope, element, attrs) ->
      attrs.$observe 'docflowHideValidation', ((v) ->
        $scope.hideValidation = (v == 'true')
        return)
      return)
  )]

module.directive 'docflowWidgetSubtable', [
  '$docflow', '$http', '$timeout', '$log',
  (($docflow, $http, $timeout, $log) ->
    scope: true
    restrict: 'A'
    require: 'ngModel'
    link: (($scope, element, attrs, ngModel) ->

      params = {}

      # Params for ui-sortable
      $scope.sortableOptions =
        handle: '.fa-ellipsis-v'
        axis: 'y'

      if attrs.docflowWidgetSubtable
        angular.extend params, $scope.$eval attrs.docflowWidgetSubtable

      if !params.field
        $log.error 'Missing \'field\' attribute'
        return

      required = angular.isDefined attrs.required

      # Watch _n for the subtable.  Presents of this value switches between view and edit modes.
      $scope.$parent.$watch "item._n.#{params.field}", ((newLinePrototype)->

        watchNewline?(); watchNewline = null
        watchSourceCollection?(); watchSourceCollection = null

        if !newLinePrototype # View only
          $scope.list = if $scope.$parent.item then $scope.item[params.field] else [] # If item is null then show an empty list
          return

        # Copy existing list
        $scope.list = list = (item for item in $scope.$parent.item[params.field])

        # Add new line
        $scope.newline = newline = angular.copy newLinePrototype
        list.push newline

        # Watch new line.  If it gets modified then add another one
        watchNewline = $scope.$watch 'newline', ((n) ->
          if newline
            newline = null
          else
            $scope.$parent.item[params.field].push $scope.newline
            $scope.newline = newline = angular.copy newLinePrototype
            list.push newline
          return), true

        # Remove a row of subtable
        $scope.remove = (item) ->
          if item != newline
            model = $scope.$parent.item[params.field]
            ind = model.indexOf item
            model.splice ind, 1
            list.splice ind, 1

        # Updates element of subtable model by new data.  Incoming data is matched to existing model by 'id' attr.
        modelUpdated = ((newModel) ->
          $scope.list = list = (item for item in newModel)
          list.push $scope.newline # Place old 'newline' to new list of elements
          $scope.$parent.item[params.field] = newModel
          return)

        # Watch source collection.  When collection is changes, update subtable list
        watchSourceCollection = $scope.$parent.$watch "item.#{params.field}", ((fld) ->

          if required
            ngModel.$setValidity 'required', fld.length != 0

          if fld.length + 1 != list.length # New list has different length, so invoke update
            modelUpdated fld
            return

          for e, i in fld
            if e != list[i] # Element object was changed, so invoke update
              modelUpdated fld
              return

          return), true
        return), false
      return)
  )]
