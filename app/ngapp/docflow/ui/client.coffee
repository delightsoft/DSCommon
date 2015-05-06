module = angular.module 'docflow.ui.client', [
  'docflow.config'
  'docflow.ui.utils'
  'docflow.ui.httpListener'
  'docflow.ui.actions'
]

module.factory '$docflowClient',
  ['$docflowActions', '$docflowUtils', '$docflowConfig', '$http', '$rootScope', '$document', '$q',
  (($docflowActions, $docflowUtils, $docflowConfig, $http, $rootScope, $document, $q) ->

    currentAction = null
    createdDoc = null
    formDoc = null

    return {

      list: ((options) ->
        # TODO: Implement base on Table directive
        return)

      get: ((id, options) ->

        # traffic optimization. can return most recently create document without requesting server
        if createdDoc?.id == id
          [t, createdDoc] = [createdDoc, null]
          deferred = $q.defer()
          deferred.resolve(t)
          return deferred.promise
        createdDoc = null

        query = []
        if options
          for p, v of options
            query.push "#{p}=#{v}" if angular.isDefined v

        url = "/api/get/#{id}?#{query.join '&'}&#{$docflowConfig.apiParams}"

        return $http(method: 'GET', url: url)
        .then((
          (resp) -> # 200
            return resp.data),
          ((resp) -> # An error
            throw Error "Failed to get document with id '#{id}' (Code: #{resp.status})")))

      # id - document full id or just docType, in case of service actions or 'create'
      # name - action name
      # options:
      #  - params - action parameters
      #  - doc - action incoming doc
      #  - t - template, to render action 'doc'. Default
      #  - tr - template, to render action 'result'
      #  - r - document revision, required for actions involing 'update'
      action: ((id, name, options) ->

        docConfig = $docflowConfig.docs[$docflowUtils.docType(id)]
        if not docConfig
          throw Error "Doctype #{$docflowUtils.docType(id)} not in docflowConfig"
        actionConfig = docConfig.actions?[name]
        if not actionConfig
          throw Error "Doctype #{$docflowUtils.docType(id)} has no action #{name} in docflowConfig"

        if currentAction
          currentAction.forceShowMessage?()
          return $q.reject('Another action is currenly running', currentAction)

        query = []
        for p, v of options
          query.push "#{p}=#{v}" if angular.isDefined v unless p == 'params' || p == 'doc' || p == 'message'

        url = "/api/action/#{id}/#{name}?#{query.join '&'}&#{$docflowConfig.apiParams}"

        if options
          data = {}
          data.params = options.params if options.params
          data.doc = options.doc if options.doc
          showMessage = !angular.isDefined(options.message) || options.message
        else
          showMessage = true

        if (showMessage)
          deferred = $q.defer()
          $docflowActions.send currentAction = new $docflowActions.Action(id, name, deferred.promise)

        return $http(method: 'POST', url: url, data: data)
        .then(
          ((resp) ->
            data = resp.data
            if data.code == 'Ok'
              if name == 'create'
                createdDoc = data.doc
              if data.file
                $("<iframe>").hide().prop("src", "/api/download/#{data.file}").appendTo("body");
              if deferred
                deferred.resolve(data.message)
                currentAction = null
              return data
            else
              if deferred
                deferred.reject(data.message)
                currentAction = null
              return $q.reject(resp)
          ),
          ((resp) ->
            # TODO: Later, provide more details on Http level error
            if deferred
              deferred.reject()
              currentAction = null
            return
          )))
      })]
