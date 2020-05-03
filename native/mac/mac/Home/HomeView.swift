//
//  ContentView.swift
//  mac
//
//  Created by Saket Narayan on 4/15/20.
//  Copyright © 2020 Saket Narayan. All rights reserved.
//

import Combine
import SwiftUI
import shared

struct HomeView: View {
  private let presenter: HomePresenter
  @State private var model: HomeUiModel

  var body: some View {
    List {
      ForEach(model.notes) { (note: HomeUiModel.Note) in
        NoteRowView(note: note)
      }
    }
      .frame(maxWidth: .infinity, maxHeight: .infinity)
      .onReceive(presenter.uiModels()) { model in
        self.model = model
      }
  }

  init(presenterFactory: HomePresenterFactory) {
    let args = HomePresenter.Args(includeEmptyNotes: true)
    presenter = presenterFactory.create(args: args)
    _model = State(initialValue: presenter.defaultUiModel())
  }
}

extension HomeUiModel.Note: Identifiable {
  public var id: Int64 {
    self.adapterId
  }
}

// TODO(saket): can this be made to work by creating a fake presenter?
//struct HomeView_Previews: PreviewProvider {
//  static var previews: some View {
//    HomeView()
//  }
//}
